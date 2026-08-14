import http from 'k6/http';
import { check } from 'k6';

export const options = {
    stages: [
            { duration: '30s', target: 5 },   // Warm-up
            { duration: '1m', target: 10 },   // Baseline
            { duration: '30s', target: 20 },  // Intermediate
            { duration: '2m', target: 30 },   // Sustained 30 VUs
            { duration: '30s', target: 0 },   // Ramp down
    ],

    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const JWT_TOKEN = __ENV.JWT_TOKEN;

export default function () {

    const payload = JSON.stringify({
        targetUrl: 'http://localhost:9999/blackhole',

        payload: JSON.stringify({
            event: 'performance-test',
            userId: __VU,
            iteration: __ITER,
        }),

        scheduledTime: '2026-08-20T12:00:00',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
            'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}`,
        },
    };

    const response = http.post(
        `${BASE_URL}/api/notifications`,
        payload,
        params
    );

    check(response, {
        'status is 201': (r) => r.status === 201,
    });
}