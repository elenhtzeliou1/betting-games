# 1. FOLDER: secureRandomGenerator

## - SecureRandomNumberGeneratorServer.java:
    Multithreaded TCP Server.
    Can accept multiple worker requests simuteniously.
    The Requests that executes: 
        - Register Game
        - Delete Game
        - Get Number
    
        It stores a HashMap<String, RNGContext> that stores the games and their buffers,producer etc.
    Via handleWorkerRequest() method it handle the Workers requests. When a worker request's registering a game the SRNGServer performs basic validations about the format of the line protocol that the worker send.
    It checks if GameName, gameSecret exists and the requested buffer size is greater than 0.
    If all the above contitions are satisfied the SRNGServer continues with adding the game in the HashMap: games. 
    If the contitions are not satisfied the method throw the approprieate errors.
    With this acction simuteinusly the Producer starts filling the buffer.
    Sychronized is used to lock games HashMap in order to not let any other thread perform updates the same time, in order to secure that the data remains valid.
    (add more info about this later)

        Via handleGameDeletion() method the SRNGServer removes, from the games HashMap, the requested for delete game. And also stops the producer for generating numbers and interrupts the producer thread.
    If anything fails in the process it throws the apropriate errors.
    
        Via hadnleGetNumber() method, the SRNGServer, performs basic validations about the line protocol that the worker used 
    to send the request. If the protocol is not valid it throws the appropriete erros. If the protocol is valid the server procides with getting the first number of the buffer that this game has stored. 
    In this process sychronized method is used in order to keep the data valid and handle the case that multiple players request to play in the same game at the same time. If this happen we ensure with sychronize() that everyone will retrieve the
    correct number from the buffer and that the buffer will fill correctly too.
    It also perform using the HashHelper validation of the secret key that the game has as the given assigment requested.
    If error occures in the process the error is send to the Worker. If not the correct number is send to the worker alongside the Secret.



## - RNGConext.java:
    Represents the SRNG State of ONE Game. It holds everything that SRNG Server needs for THIS game.
    
## - Buffer.java:
    Represents the bounded queue that stores the random generated numbers that the producer generates.
    It secures that the producer cannot generate more numbers exciding the assigned buffer size.
    It secures that the consumer (Worker) can not pull number if the buffer is empty (bufferSize = 0)

## - Producer.java:
    Generates random integers and stores them inside the buffer.

## - HashHelper.java:
    Shared utility that lets both sides (Worker and SRNG) to execute the same hash calculation.

------

# 2. FOLDER: consoleApps

## - ManagerConsoleApp.java
    Stores all the Manager Console Logic.
    Holds and prints Manager menu.
    Sends Manager's requests to MasterServer.
    Print results back to manager console.

    Actions that the Manager can perform at this point: 
        1. Add new game by providing the JSON file path
        2. Delete existing game
        3. Update game risk
        4. Show all existing games
        
    Should add:
        1. Set game visibility back to true (make it visible to player)
        2. Show specific provider profits/losses
        3. Show specific player profits/losses
        

## - DummyPlayerApp.java
    Store the Player logic
    Holds and prints Player Menu.
    Sends Player's requests to MasterServer
    Print the results back to Player's console.

    Actions that the Player can perform at this point:
        1. See all available games
        2. Search() using filters
        3. Play to a game
        4. Rate an available game   
        5. Add tokens to account
        6. See his total tokens
    
    Should add:
        3. Add/Subtract profit/loss from player balance