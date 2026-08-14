import http from 'k6/http';
import { check } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 5 },   // Warm-up
        { duration: '1m', target: 15 },   // Baseline
        { duration: '1m', target: 15 },   // Sustained mixed workload
        { duration: '30s', target: 0 },   // Ramp down
    ],

    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN;

export default function () {

    // Every iteration performs one POST.
    const postPayload = JSON.stringify({
        targetUrl: 'http://localhost:9999/blackhole',

        payload: JSON.stringify({
            event: 'mix-test',
            userId: __VU,
            iteration: __ITER,
        }),

        scheduledTime: '2026-08-20T12:00:00',
    });

    const postParams = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
            'Idempotency-Key': `k6-mix-${__VU}-${__ITER}-${Date.now()}`,
        },

        tags: {
            name: 'POST_notification',
        },
    };

    const postResponse = http.post(
        `${BASE_URL}/api/notifications`,
        postPayload,
        postParams
    );

    const postSuccessful = check(postResponse, {
        'POST status is 201': (r) => r.status === 201,
    });


    /*
     * 25% of successful POSTs are followed by a GET.
     *
     * Therefore:
     *
     * 100 POST requests
     *  25 GET requests
     *
     * Total = 125 requests
     *
     * POST = 100 / 125 = 80%
     * GET  =  25 / 125 = 20%
     */
    if (postSuccessful && Math.random() < 0.25) {

        const notificationId = postResponse.json('id');

        if (notificationId) {

            const getParams = {
                headers: {
                    'Authorization': `Bearer ${JWT_TOKEN}`,
                },

                tags: {
                    name: 'GET_notification',
                },
            };

            const getResponse = http.get(
                `${BASE_URL}/api/notifications/${notificationId}`,
                getParams
            );

            check(getResponse, {
                'GET status is 200': (r) => r.status === 200,
            });
        }
    }
}