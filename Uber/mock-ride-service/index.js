const http = require("http");

const rides = {
    // F4 test rides
    1:  { id: 1,  userId: 2, driverId: 1, amount: 200.0, status: "PAYMENT_PENDING" },
    2:  { id: 2,  userId: 2, driverId: 1, amount: 150.0, status: "ACCEPTED" },        // F4-2 wrong status → 400
    3:  { id: 3,  userId: 2, driverId: 1, amount: 200.0, status: "PAYMENT_PENDING" }, // F4-1 happy path

    // F10 rides — must match driverIds in your driver DB (2,4=SEDAN, 3=SUV)
    10: { id: 10, userId: 2, driverId: 2, amount: 200.0, status: "PAYMENT_PENDING" },
    11: { id: 11, userId: 2, driverId: 2, amount: 150.0, status: "PAYMENT_PENDING" },
    12: { id: 12, userId: 2, driverId: 2, amount: 150.0, status: "ACCEPTED" },
    20: { id: 20, userId: 2, driverId: 2, amount: 200.0, status: "COMPLETED" },
    21: { id: 21, userId: 2, driverId: 4, amount: 200.0, status: "COMPLETED" },
    22: { id: 22, userId: 2, driverId: 4, amount: 200.0, status: "COMPLETED" },
    23: { id: 23, userId: 2, driverId: 3, amount: 200.0, status: "COMPLETED" },
    24: { id: 24, userId: 2, driverId: 3, amount: 200.0, status: "COMPLETED" },
};

http.createServer((req, res) => {
    console.log("→ " + req.method + " " + req.url);
    const match = req.url.match(/^\/api\/rides\/(\d+)$/);
    if (match) {
        const ride = rides[Number(match[1])];
        if (ride) {
            res.writeHead(200, { "Content-Type": "application/json" });
            res.end(JSON.stringify(ride));
        } else {
            res.writeHead(404, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ message: "Ride not found" }));
        }
    } else {
        res.writeHead(404);
        res.end();
    }
}).listen(8080, () => console.log("Mock ride-service running on :8080"));