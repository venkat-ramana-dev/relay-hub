import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        gentle_seed: {
            executor: 'constant-arrival-rate',
            // How many requests to send
            rate: 20,
            // Per how many units of time (1s = 20 requests per second)
            timeUnit: '1s',
            // How long the test runs (20 * 500 = 10000 total notifications)
            duration: '500s',
            preAllocatedVUs: 5,
            maxVUs: 10,
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN;

export default function () {
    const payload = JSON.stringify({
        targetUrl: 'http://localhost:8089/webhook', // Targeting WireMock!
        payload: JSON.stringify({ event: 'drain-test', id: __ITER })
    });

    const params = {
        timeout: '5s',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
            'Idempotency-Key': `seed-gentle-${__ITER}-${Date.now()}`,
        },
    };

    const res = http.post(`${BASE_URL}/api/notifications`, payload, params);

    check(res, {
        'status is 201': (r) => r.status === 201,
    });
}