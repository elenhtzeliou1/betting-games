# Distributed Online Betting Platform
Course project for Distributed Systems, Department of Informatics,
Athens University of Economics and Business (AUEB)
Spring Semester 2025–2026

Overview

This project implements a distributed online betting platform in Java. The system supports two main roles:

 - Manager
    - Add / hide / restore / modify games
    - View game statistics
    - View profit/loss aggregations through MapReduce
 - Player
    - Browse available games
    - Search using filters
    - Play games with a chosen bet
    - Rate games
    - Add tokens and view balance

The backend is split across multiple machines and components:
- MasterServer
- WorkerServers
- ReducerServer
- Secure Random Generator Server (SRNG)
- Manager Console App
- Dummy Player App

All communication between backend nodes is implemented exclusively with TCP sockets, as required by the assignment.

---

# Architecture

## 1. MasterServer
The central coordination node of the system.

Responsibilities:

- Accepts requests from the Manager app and Player app
- Routes each game to the correct Worker using hashing:
  - workerIndex = H(gameName) mod numberOfWorkers
- For search and aggregation requests, coordinates MapReduce
- For play requests, forwards the request to the Worker that owns the game
- Receives final Reduce results back from the ReducerServer

## 2. WorkerServer
The main data-processing node.

Responsibilities: 

* Stores games in memory
* Processes manager commands such as add / modify / delete / restore
* Processes player commands such as search, rate, and play
* Communicates with:
  * the ReducerServer during MapReduce jobs
  * the Secure Random Generator Server during play requests

Each Worker is **multithreaded** and can process multiple requests from the Master in parallel.

## 3. ReducerServer
The Reduce side of the MapReduce pipeline.

Responsibilities:

* Accepts partial map results from Workers
* Merges the partial results in memory
* Detects when all expected Workers have submitted results
* Pushes final results back to the MasterServer

Supported job types:

* Search
* Provider profit/loss
* Player profit/loss

## 4. Secure Random Generator Server (SRNG)
A separate multithreaded TCP server responsible for secure random number generation.

Responsibilities:

* Maintains one random-number pipeline per game
* Uses a producer-consumer model with a bounded buffer
* Sends back:
  * a random integer
  * sha256(number + secret)

The Worker verifies this hash locally before using the number.

## 5. Manager Console App
A console-based interface for manager operations.

## 6. Dummy Player App
A console-based interface for player operations used in the backend phase of the assignment.

---

# Main Design Decisions

## 1. Distributed game placement
Games are assigned to Workers deterministically using the game name:
    * NodeId = H(GameName) mod NumberOfWorkers

This ensures:

* predictable routing
* balanced distribution
* no need for a central game database

## 2. In-memory storage

All game data is stored in memory on the appropriate Worker, as required by the assignment.

## 3. Producer-consumer SRNG

Because secure random generation has latency, each game has:

* its own bounded buffer
* its own producer thread
* a consumer side used by the Worker during PLAY

This avoids generating the random value synchronously at play time.

## 4. Hash-based integrity check

For each play request:
1. Worker requests a random number from SRNG
2. SRNG returns number and sha256(number + secret)
3. Worker recomputes the same hash locally
4. If hashes match, the number is trusted

---


# Running the System (2 computers)

Currently tested in just 2 different computers: 

## On PC
1. Start MasterServer
    ```bash
   java -cp "target\classes;target\dependency\*" backend.master.MasterServer 5000 192.168.1.107:7000 192.168.1.103:6001 192.168.1.107:6002 192.168.1.107:6003
    ```
3. Start Worker 1
```bash
   java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6001 192.168.1.107 8000
```
3. Start Manager Console App
```bash
   java -cp "target\classes;target\dependency\*" backend.consoleApps.ManagerConsoleApp 192.168.1.103 5000
```
4. Start Dummy Player App
```bash
   java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp 192.168.1.103 5000
```

## On Laptop
1. Start ReducerServer
 ```bash
   java -cp "target\classes;target\dependency\*" backend.reducer.ReducerServer 7000 192.168.1.103 5001
```

2. Start SRNG Server
 ```bash
   java -cp "target\classes;target\dependency\*" backend.secureRandomGenerator.SecureRandomNumberGeneratorServer 8000 0.0.0.0
```

3. Start Worker 2
 ```bash
   java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6002 192.168.1.107 8000
```

4. Start Worker 3
 ```bash
   java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6003 192.168.1.107 8000
```

---

# Run the system (3 Computers)

## Replace these placeholders first

- `<PC1_IP>` = IP of Computer 1  
  Runs: `MasterServer`, `SecureRandomNumberGeneratorServer`, `DummyPlayerApp`

- `<PC2_IP>` = IP of Computer 2  
  Runs: `WorkerServer`(s), `ManagerConsoleApp`

- `<PC3_IP>` = IP of Computer 3  
  Runs: `ReducerServer`, `DummyPlayerApp`

### Mapping of service IPs

- `<MASTER_IP>` = `<PC1_IP>`
- `<SRNG_IP>` = `<PC1_IP>`
- `<WORKER1_IP>` = `<PC2_IP>`
- `<WORKER2_IP>` = `<PC2_IP>`
- `<WORKER3_IP>` = `<PC2_IP>`
- `<REDUCER_IP>` = `<PC3_IP>`

## On PC

## Computer 1
Runs: MasterServer, SRNG, Dummy Player

### 1. Start SecureRandomNumberGeneratorServer
```bash
java -cp "target\classes;target\dependency\*" backend.secureRandomGenerator.SecureRandomNumberGeneratorServer 8000 0.0.0.0
```
### 2. Start MasterServer
```bash
java -cp "target\classes;target\dependency\*" backend.master.MasterServer 5000 <REDUCER_IP>:7000 <WORKER1_IP>:6001 <WORKER2_IP>:6002 <WORKER3_IP>:6003
```
### 3. Start DummyPlayerApp
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp <MASTER_IP> 5000
```
## Computer 2
Runs: Workers, Manager
### 1. Start Worker 1
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6001 <SRNG_IP> 8000
```
### 2. Start Worker 2
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6002 <SRNG_IP> 8000
```
### 3. Start Worker 3
```bash
java -cp "target\classes;target\dependency\*" backend.worker.WorkerServer 6003 <SRNG_IP> 8000
```
### 4. Start Manager Console App
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.ManagerConsoleApp <MASTER_IP> 5000
```

## Computer 3
Runs: Reducer, Dummy Player

### 1. Start ReducerServer
```bash
java -cp "target\classes;target\dependency\*" backend.reducer.ReducerServer 7000 <MASTER_IP> 5001
```
### 2. Start Dummy Player App
```bash
java -cp "target\classes;target\dependency\*" backend.consoleApps.DummyPlayerApp <MASTER_IP> 5000
```
---
!!! Important notes (For me)
MasterServer listens on port 5000.
MasterServer also opens the reducer callback on port 5001.
ReducerServer must point back to <MASTER_IP> 5001.
All workers must point to the SRNG machine, so they use <SRNG_IP> 8000.
Both Manager and Dummy Players always connect to the Master, so they use <MASTER_IP> 5000.

---

# Build
This project uses Maven

Compile the project with: 
```bash
mvn clean package
mvn dependency:copy-dependencies
```

---

# Recommended Startup Order
We recomment to start the components in the following order:

1. MasterServer
2. ReducerServer
3. SecureRandomGeneratorServer
4. All WorkerServers
5. ManagerConsoleApp / DummyPlayerApp
