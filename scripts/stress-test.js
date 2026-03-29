const axios = require('axios');

const API_BASE_URL = 'http://localhost:8080/api/v1';
const CONCURRENCY = 10; // Number of parallel batches to avoid overloading the OS ports
const TOTAL_USERS = 100;
const RUN_ID = Date.now();

async function simulateUser(id) {
    const userEmail = `stress-user-${id}-${RUN_ID}@test.com`;
    const userPassword = 'Password123!';
    const userName = `Stress User ${id}`;

    const client = axios.create({
        baseURL: API_BASE_URL,
        headers: {
            'Content-Type': 'application/json'
        }
    });

    try {
        // 1. Register
        console.log(`[User ${id}] Registering...`);
        await client.post('/auth/register', {
            email: userEmail,
            fullName: userName,
            password: userPassword
        });

        // 2. Login
        console.log(`[User ${id}] Logging in...`);
        const loginResp = await client.post('/auth/login', {
            email: userEmail,
            password: userPassword
        });
        const token = loginResp.data.token;
        client.defaults.headers.common['Authorization'] = `Bearer ${token}`;

        // 3. Upgrade to Standard (Brutal Step)
        console.log(`[User ${id}] Upgrading to Standard...`);
        await client.post('/payments/create-order', {
            tier: 'STANDARD',
            amount: 0 // Free upgrade for simulation
        });

        // 4. Update Business Profile
        console.log(`[User ${id}] Setting up profile...`);
        await client.put('/profile', {
            businessName: `Business ${id}`,
            niche: 'Fashion',
            brandTone: 'Modern',
            targetAudience: 'Gen Z'
        });

        // 5. Generate AI Content (Stress Step)
        console.log(`[User ${id}] Generating AI post...`);
        const startTime = Date.now();
        const genResp = await client.post('/ai/generate', {
            command: 'Create a summer collection post',
            count: 1
        });
        const endTime = Date.now();

        console.log(`[User ${id}] ✅ SUCCESS! AI Generation took ${endTime - startTime}ms`);
        return true;
    } catch (err) {
        console.error(`[User ${id}] ❌ FAILED at step: ${err.config?.url || 'unknown'} - Error: ${err.response?.data?.message || err.message}`);
        return false;
    }
}

async function runBrutalTest() {
    console.log(`🚀 Starting BRUTAL STRESS TEST for ${TOTAL_USERS} users...`);
    const startTimeTotal = Date.now();
    let successCount = 0;

    // Run in chunks to manage OS resources
    for (let i = 1; i <= TOTAL_USERS; i += CONCURRENCY) {
        const batch = [];
        for (let j = 0; j < CONCURRENCY && (i + j) <= TOTAL_USERS; j++) {
            batch.push(simulateUser(i + j));
        }
        const results = await Promise.all(batch);
        successCount += results.filter(r => r).length;
    }

    const endTimeTotal = Date.now();
    console.log(`\n================================`);
    console.log(`🏁 TEST COMPLETED`);
    console.log(`📊 Success: ${successCount}/${TOTAL_USERS}`);
    console.log(`⏱️ Total Time: ${(endTimeTotal - startTimeTotal) / 1000}s`);
    console.log(`================================`);
}

runBrutalTest();
