# Distributed Online Betting Platform
Course project for Distributed Systems, Department of Informatics,
Athens University of Economics and Business (AUEB)
Spring Semester 2025–2026

## Overview

This project implements a distributed online betting platform in Java. The system supports two main roles:

**Manager**
- Add / hide / restore / modify games
- View game statistics
- View profit/loss aggregations through MapReduce

**Player**
- Browse available games
- Search using filters
- Play games with a chosen bet
- Rate games (1–5 stars)
- Add tokens and view balance

The backend is split across multiple machines and components:
- MasterServer
- WorkerServers
- ReducerServer
- Secure Random Generator Server (SRNG)
- Manager Console App
- Dummy Player App (backend testing)
- Android App (full player interface — Part B)

All communication between backend nodes is implemented exclusively with TCP sockets, as required by the assignment.

---

# Architecture

## 1. MasterServer
The central coordination node of the system.

Responsibilities:
- Accepts requests from the Manager app and Player app (console or Android)
- Routes each game to the correct Worker using hashing:
    - `workerIndex = H(gameName) mod numberOfWorkers`
- For search and aggregation requests, coordinates MapReduce
- For play requests, forwards the request to the Worker that owns the game
- Receives final Reduce results back from the ReducerServer

## 2. WorkerServer
The main data-processing node.

Responsibilities:
- Stores games in memory
- Processes manager commands: add / modify / delete / restore
- Processes player commands: search, rate, play, get user ratings
- Communicates with:
    - the ReducerServer during MapReduce jobs
    - the Secure Random Generator Server during play requests

Each Worker is **multithreaded** and can process multiple requests from the Master in parallel.

## 3. ReducerServer
The Reduce side of the MapReduce pipeline.

Responsibilities:
- Accepts partial map results from Workers
- Merges the partial results in memory
- Detects when all expected Workers have submitted results
- Pushes final results back to the MasterServer

Supported job types:
- Search
- Provider profit/loss
- Player profit/loss
- User ratings (GET_USER_RATINGS)

## 4. Secure Random Generator Server (SRNG)
A separate multithreaded TCP server responsible for secure random number generation.

Responsibilities:
- Maintains one random-number pipeline per game
- Uses a producer-consumer model with a bounded buffer
- Sends back a random integer and `sha256(number + secret)`

The Worker verifies this hash locally before using the number.

## 5. Manager Console App
A console-based interface for manager operations (add/edit/delete games, view profit/loss).

## 6. Dummy Player App
A console-based interface for player operations used during backend testing (Part A).

## 7. Android App
A native Android application that serves as the full player interface (Part B).

Features:
- Sign in with a player ID
- Browse all available games (swipe to refresh)
- Search and filter games by stars, bet category, and risk level
- View game details in a bottom sheet (min bet, max bet, jackpot, risk)
- Play a game with adjustable bet (± 0.01 steps, clamped to min/max)
- Rate a game (1–5 stars, one rating per player per game, synced from server on login)
- Deposit tokens
- View and refresh balance
- Logout (clears session and ratings cache)

The Android app connects to the MasterServer over a persistent TCP socket. All network calls run on background threads; the UI is updated on the main thread via callbacks.

---

# Main Design Decisions

## 1. Distributed game placement
Games are assigned to Workers deterministically using the game name:
```
NodeId = H(GameName) mod NumberOfWorkers
```
This ensures predictable routing, balanced distribution, and no need for a central game database.

## 2. Active Replication
Each game is stored on `REPLICATION_FACTOR` workers (default: 2 — one primary, one replica).
- All write operations (add, modify, delete, rate, sync-play) are applied to all replicas.
- Read operations (play, search) try the primary first, then fall back to a replica if the primary is unreachable.
- MapReduce jobs skip down workers and adjust `expectedN` accordingly.

## 3. In-memory storage
All game data is stored in memory on the appropriate Worker, as required by the assignment. Game logos may optionally be stored on disk.

## 4. Producer-consumer SRNG
Because secure random generation has latency, each game has:
- its own bounded buffer
- its own producer thread
- a consumer side used by the Worker during PLAY

This avoids generating the random value synchronously at play time.

## 5. Hash-based integrity check
For each play request:
1. Worker requests a random number from SRNG
2. SRNG returns number and `sha256(number + secret)`
3. Worker recomputes the same hash locally
4. If hashes match, the number is trusted

## 6. Ratings synced from server
On login the Android app calls `GET_USER_RATINGS` via MapReduce to fetch all previous ratings for that player from the Workers. Ratings are cached in `AppViewModel` for the session and cleared on logout — no local storage is used.

---

# Project Structure

```
betting-games/
├── backend/                        # Java backend (Maven)
│   └── src/main/java/backend/
│       ├── common/                 # Shared models (Game, GameState, BetRecord, ...)
│       ├── consoleApps/            # ManagerConsoleApp, DummyPlayerApp
│       ├── master/                 # MasterServer
│       ├── reducer/                # ReducerServer + Job classes
│       ├── secureRandomGenerator/  # SRNG server
│       └── worker/                 # WorkerServer
└── android/                        # Android app (Gradle)
    └── app/src/main/java/com/example/bettingapp/
        ├── activities/             # CasinoActivity, PlayActivity, RateActivity, ...
        ├── adapters/               # GameAdapter, GameViewHolder
        ├── fragments/              # GamesFragment, AccountFragment, ...
        ├── model/                  # SearchResult, BetResult
        ├── network/                # MasterConnection, MasterProtocol, SocketTask
        └── viewmodel/              # AppViewModel, GamesViewModel
```

---

# Build

## Backend (Maven)
```bash
cd backend
mvn clean package
mvn dependency:copy-dependencies
```

## Android
Open the `android/` folder in Android Studio and click **Run**, or build from the command line:
```bash
cd android
./gradlew assembleDebug
```
The APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

Before building, set the MasterServer IP in:
```
android/app/src/main/java/com/example/bettingapp/network/AppConfig.java
```
```java
public static final String MASTER_HOST = "<MASTER_IP>";
public static final int    MASTER_PORT = 5000;
```

---

# Running the System (2 computers)

Currently tested on 2 computers.

## On PC
### 1. Start MasterServer
```bash
java -cp "target\classes;target\dependency\*" backend.master.MasterServer 5000 192.168.1.107:7000 192.168.1.103:6001 192.168.1.107:6002 192.168.1.107:6003
```
### 2. Start Worker 1
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6001 192.168.1.107 8000
```
### 3. Start Manager Console App
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.ManagerConsoleApp 192.168.1.103 5000
```
### 4. Start Dummy Player App
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp 192.168.1.103 5000
```

## On Laptop
### 1. Start ReducerServer
```bash
java -cp "target\classes;target\dependency\*" backend.reducer.ReducerServer 7000 192.168.1.103 5001
```
### 2. Start SRNG Server
```bash
java -cp "target\classes;target\dependency\*" backend.secureRandomGenerator.SecureRandomNumberGeneratorServer 8000 0.0.0.0
```
### 3. Start Worker 2
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6002 192.168.1.107 8000
```
### 4. Start Worker 3
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6003 192.168.1.107 8000
```

## Android
Install the APK on an Android device connected to the same network, then open the app and sign in with a player ID.

---

# Running the System (3 computers)

## Replace these placeholders first

| Placeholder | Description |
|---|---|
| `<PC1_IP>` | Computer 1 — runs MasterServer, SRNG, DummyPlayerApp |
| `<PC2_IP>` | Computer 2 — runs WorkerServers, ManagerConsoleApp |
| `<PC3_IP>` | Computer 3 — runs ReducerServer, DummyPlayerApp |

## Computer 1 — Master, SRNG, Player

### 1. Start SecureRandomNumberGeneratorServer
```bash
java -cp "target\classes;target\dependency\*" backend.secureRandomGenerator.SecureRandomNumberGeneratorServer 8000 0.0.0.0
```
### 2. Start MasterServer
```bash
java -cp "target\classes;target\dependency\*" backend.master.MasterServer 5000 <PC3_IP>:7000 <PC2_IP>:6001 <PC2_IP>:6002 <PC2_IP>:6003
```
### 3. Start DummyPlayerApp
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp <PC1_IP> 5000
```

## Computer 2 — Workers, Manager

### 1. Start Worker 1
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6001 <PC1_IP> 8000
```
### 2. Start Worker 2
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6002 <PC1_IP> 8000
```
### 3. Start Worker 3
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6003 <PC1_IP> 8000
```
### 4. Start Manager Console App
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.ManagerConsoleApp <PC1_IP> 5000
```

## Computer 3 — Reducer, Player

### 1. Start ReducerServer
```bash
java -cp "target\classes;target\dependency\*" backend.reducer.ReducerServer 7000 <PC1_IP> 5001
```
### 2. Start DummyPlayerApp
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp <PC1_IP> 5000
```

## Android (any device on the same network)
Set `MASTER_HOST = "<PC1_IP>"` in `AppConfig.java`, build and install the APK.

---

# Important Notes

- MasterServer listens on port **5000**
- MasterServer opens the reducer callback on port **5001** (masterPort + 1)
- ReducerServer must point back to `<MASTER_IP> 5001`
- All Workers must point to the SRNG machine using `<SRNG_IP> 8000`
- Manager and Player apps always connect to the Master on `<MASTER_IP> 5000`
- The Android device must be on the same local network as the MasterServer

---

# Recommended Startup Order

1. SecureRandomNumberGeneratorServer
2. MasterServer
3. ReducerServer
4. All WorkerServers
5. ManagerConsoleApp / DummyPlayerApp / Android App