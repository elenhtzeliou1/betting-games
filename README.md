# FOLDER: secureRandomGenerator

## - RNGConext.java:
    Represents the SRNG State of ONE Game. It holds everything that SRNG Server needs for THIS game.
    
## - Buffer.java:
    Represents the bounded queue that stores the random generated numbers that the producer generates.
    It secures that the producer cannot generate more numbers exciding the assigned buffer size.
    It secures that the consumer (Worker) can not pull number if the buffer is empty (bufferSize = 0)

## - Producer.java:
    Generates random integers and stores them inside the buffer.

## - 