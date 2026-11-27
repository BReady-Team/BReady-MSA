import http from "k6/http";
import { check, fail, sleep } from "k6";

// 실행 전제: docker-compose-fake.yml을 겹쳐 띄운 상태(가짜 카카오 8089)
const BASE_URL = __ENV.BASE_URL || "http://localhost:8000";
const FAKE_KAKAO_URL = __ENV.FAKE_KAKAO_URL || "http://localhost:8089";
const USER_COUNT = Number(__ENV.USER_COUNT || 20);
const RECOMMEND_RATE = Number(__ENV.RECOMMEND_RATE || 12);
const BROWSE_RATE = Number(__ENV.BROWSE_RATE || 20);
const SLOW_DELAY_MS = Number(__ENV.SLOW_DELAY_MS || 3000);
const PHASE_SECONDS = Number(__ENV.PHASE_SECONDS || 60);
const PASSWORD = "passw0rd!";

const TOTAL_DURATION = `${PHASE_SECONDS * 3}s`;

// 응답이 느려져도 보내는 양이 줄지 않게 도착률을 고정 (전,후를 같은 부하로 비교)
export const options = {
    scenarios: {
        recommend: {
            executor: "constant-arrival-rate",
            exec: "recommend",
            rate: RECOMMEND_RATE,
            timeUnit: "1s",
            duration: TOTAL_DURATION,
            preAllocatedVUs: 100,
            maxVUs: 400,
        },
        browse: {
            executor: "constant-arrival-rate",
            exec: "browse",
            rate: BROWSE_RATE,
            timeUnit: "1s",
            duration: TOTAL_DURATION,
            preAllocatedVUs: 50,
            maxVUs: 400,
        },
        slow_external: {
            executor: "per-vu-iterations",
            exec: "slowExternal",
            vus: 1,
            iterations: 1,
            maxDuration: TOTAL_DURATION,
        },
    },
    // 구간·엔드포인트별 수치를 요약에 따로 찍기 위한 빈 임계값
    thresholds: {
        "http_req_duration{name:GET /plans,phase:1_normal}": [],
        "http_req_duration{name:GET /plans,phase:2_slow}": [],
        "http_req_duration{name:GET /plans,phase:3_recovered}": [],
        "http_req_duration{name:POST /recommendations/places,phase:1_normal}": [],
        "http_req_duration{name:POST /recommendations/places,phase:2_slow}": [],
        "http_req_duration{name:POST /recommendations/places,phase:3_recovered}": [],
        "http_req_failed{name:GET /plans,phase:1_normal}": [],
        "http_req_failed{name:GET /plans,phase:2_slow}": [],
        "http_req_failed{name:GET /plans,phase:3_recovered}": [],
        "http_req_failed{name:POST /recommendations/places,phase:1_normal}": [],
        "http_req_failed{name:POST /recommendations/places,phase:2_slow}": [],
        "http_req_failed{name:POST /recommendations/places,phase:3_recovered}": [],
    },
    summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

function request(method, path, body, token, tags) {
    const headers = { "Content-Type": "application/json" };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    return http.request(method, `${BASE_URL}${path}`, body ? JSON.stringify(body) : null, {
        headers,
        tags,
        timeout: "60s",
    });
}

function expectStatus(response, status, name) {
    return check(response, { [`${name} ${status}`]: (r) => r.status === status });
}

function setKakaoDelay(delayMs) {
    const response = http.post(`${FAKE_KAKAO_URL}/__admin/settings`, JSON.stringify({ fixedDelay: delayMs }), {
        tags: { name: "가짜 카카오 지연 설정" },
    });
    if (response.status !== 200) {
        fail(`가짜 카카오 지연 설정 실패 status=${response.status}. docker-compose-fake.yml로 띄웠는지 확인`);
    }
}

function prepareUser(index) {
    const runId = Date.now();
    const email = `k6reco-${runId}-${index}@local.test`;
    const setupTags = { name: "setup" };

    const signup = request("POST", "/api/v1/auth/signup",
        { nickname: `k6reco${index}x${runId % 100000}`, email, password: PASSWORD }, null, setupTags);
    if (signup.status !== 201) {
        fail(`가입 실패 status=${signup.status}. 앱이 떠 있는지 확인`);
    }
    const token = request("POST", "/api/v1/auth/login", { email, password: PASSWORD }, null, setupTags)
        .json("data.accessToken");

    const planId = request("POST", "/api/v1/plans",
        { title: "k6 추천 플랜", planDate: "2026-12-24", region: "서울" }, token, setupTags).json("data.planId");
    const categoryId = request("POST", `/api/v1/plans/${planId}/categories`,
        { categoryType: "CAFE" }, token, setupTags).json("data.planCategoryId");
    request("POST", "/api/v1/places/candidates", {
        planId,
        categoryId,
        externalId: `k6reco-${runId}-${index}`,
        name: "k6 카페",
        address: "서울 성동구",
        latitude: 37.544,
        longitude: 127.055,
        isIndoor: true,
    }, token, setupTags);
    const trigger = request("POST", "/api/v1/triggers",
        { planId, categoryId, triggerType: "FATIGUE" }, token, setupTags);
    if (trigger.status !== 200) {
        fail(`트리거 생성 실패 status=${trigger.status}`);
    }
    return { token, triggerId: trigger.json("data.triggerId") };
}

export function setup() {
    setKakaoDelay(0);
    const users = [];
    for (let i = 0; i < USER_COUNT; i++) {
        users.push(prepareUser(i));
    }
    return { users, startedAt: Date.now() };
}

export function teardown() {
    setKakaoDelay(0);
}

function currentPhase(data) {
    const elapsedSeconds = (Date.now() - data.startedAt) / 1000;
    if (elapsedSeconds < PHASE_SECONDS) {
        return "1_normal";
    }
    if (elapsedSeconds < PHASE_SECONDS * 2) {
        return "2_slow";
    }
    return "3_recovered";
}

function pickUser(data) {
    return data.users[Math.floor(Math.random() * data.users.length)];
}

function randomCoordinateQuery() {
    const latitude = (33 + Math.random() * 5).toFixed(4);
    const longitude = (126 + Math.random() * 3).toFixed(4);
    return `latitude=${latitude}&longitude=${longitude}`;
}

export function recommend(data) {
    const user = pickUser(data);
    const name = "POST /recommendations/places";
    const response = request("POST", `/api/v1/recommendations/places?${randomCoordinateQuery()}`,
        { triggerId: user.triggerId }, user.token, { name, phase: currentPhase(data) });
    expectStatus(response, 200, name);
}

// 추천과 무관한 조회 -> 외부를 부르지 않고 DB만 읽는다
export function browse(data) {
    const user = pickUser(data);
    const name = "GET /plans";
    const response = request("GET", "/api/v1/plans?page=0&size=10", null, user.token,
        { name, phase: currentPhase(data) });
    expectStatus(response, 200, name);
}

// 가운데 구간에서만 가짜 카카오를 느리게 만든다
export function slowExternal() {
    sleep(PHASE_SECONDS);
    setKakaoDelay(SLOW_DELAY_MS);
    console.log(`가짜 카카오 지연 ${SLOW_DELAY_MS}ms 시작`);
    sleep(PHASE_SECONDS);
    setKakaoDelay(0);
    console.log("가짜 카카오 지연 해제");
}
