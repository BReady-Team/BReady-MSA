import http from "k6/http";
import { check, fail, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8000";
const USER_COUNT = Number(__ENV.USER_COUNT || 20);
const PASSWORD = "passw0rd!";

export const options = {
    scenarios: {
        user_flow: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: 20 },
                { duration: "1m", target: 20 },
                { duration: "30s", target: 50 },
                { duration: "1m", target: 50 },
                { duration: "20s", target: 0 },
            ],
            gracefulRampDown: "10s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"],
    },
};

function jsonHeaders(token) {
    const headers = { "Content-Type": "application/json" };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    return { headers };
}

function request(method, path, body, token, name) {
    const params = Object.assign(jsonHeaders(token), { tags: { name } });
    return http.request(method, `${BASE_URL}${path}`, body ? JSON.stringify(body) : null, params);
}

function expectStatus(response, status, name) {
    const ok = check(response, { [`${name} ${status}`]: (r) => r.status === status });
    if (!ok) {
        console.warn(`${name} 실패 status=${response.status}`);
    }
    return ok;
}

function signupAndLogin(index) {
    const runId = Date.now();
    const email = `k6-${runId}-${index}@local.test`;
    const signup = request("POST", "/api/v1/auth/signup",
        { nickname: `k6user${index}x${runId % 100000}`, email, password: PASSWORD }, null, "POST /auth/signup");
    if (!expectStatus(signup, 201, "POST /auth/signup")) {
        fail("가입 실패. 앱이 떠 있는지 확인");
    }
    const login = request("POST", "/api/v1/auth/login", { email, password: PASSWORD }, null, "POST /auth/login");
    if (!expectStatus(login, 200, "POST /auth/login")) {
        fail("로그인 실패");
    }
    return login.json("data.accessToken");
}

function createPlanWithCandidate(token) {
    const plan = request("POST", "/api/v1/plans",
        { title: "k6 플랜", planDate: "2026-12-24", region: "서울" }, token, "POST /plans");
    if (!expectStatus(plan, 201, "POST /plans")) {
        return null;
    }
    const planId = plan.json("data.planId");

    const category = request("POST", `/api/v1/plans/${planId}/categories`,
        { categoryType: "CAFE" }, token, "POST /plans/{id}/categories");
    if (!expectStatus(category, 201, "POST /plans/{id}/categories")) {
        return null;
    }
    const categoryId = category.json("data.planCategoryId");

    // externalId가 매번 달라서 Catalog에 새 장소가 등록(모놀리식 → Catalog HTTP)
    const candidate = request("POST", "/api/v1/places/candidates", {
        planId,
        categoryId,
        externalId: `k6-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
        name: "k6 카페",
        address: "서울 성동구",
        latitude: 37.544,
        longitude: 127.055,
        isIndoor: true,
    }, token, "POST /places/candidates");
    // 후보/트리거 생성은 옛 계약대로 200 반환
    expectStatus(candidate, 200, "POST /places/candidates");

    return { planId, categoryId };
}

export function setup() {
    const users = [];
    for (let i = 0; i < USER_COUNT; i++) {
        const token = signupAndLogin(i);
        const plan = createPlanWithCandidate(token);
        if (!plan) {
            fail("준비 데이터 생성 실패");
        }
        users.push({ token, planId: plan.planId });
    }
    return { users };
}

function browse(user) {
    expectStatus(request("GET", "/api/v1/plans?page=0&size=10", null, user.token, "GET /plans"), 200, "GET /plans");
    sleep(1);
    expectStatus(request("GET", `/api/v1/plans/${user.planId}`, null, user.token, "GET /plans/{id}"),
        200, "GET /plans/{id}");
    sleep(1);
    expectStatus(request("GET", "/api/v1/stats/summary?period=ALL", null, user.token, "GET /stats/summary"),
        200, "GET /stats/summary");
}

function makePlan(user) {
    const created = createPlanWithCandidate(user.token);
    if (!created) {
        return;
    }
    sleep(1);
    // 트리거를 만들면 커밋 뒤 Async 리스너가 통계 갱신
    expectStatus(request("POST", "/api/v1/triggers",
        { planId: created.planId, categoryId: created.categoryId, triggerType: "WAITING_TOO_LONG" },
        user.token, "POST /triggers"), 200, "POST /triggers");
}

export default function (data) {
    const user = data.users[(__VU - 1) % data.users.length];
    if (Math.random() < 0.8) {
        browse(user);
    } else {
        makePlan(user);
    }
    sleep(1);
}
