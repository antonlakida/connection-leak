# DB connection leak problem

## Background

In one of our services we are using Exposed as query mapping framework.
Lately we found out that on our environments where the Actuator health 
check is used, application eventually runs out of free DB connections.

We tried to reproduce it and it looks like the `DataSourceHealthIndicator`
does not return the connection to the pool, but only when used together 
with Exposed.

## Test

The test `ConnectionLeakApplicationTests` reproduces the problem, at 
the beginning there are 10 connections available, after we run the test,
which initiates a transaction through Exposed and runs the health check,
all the connections from the pool are consumed. And they don't become
available with time, we checked that.

This is tested with Kotlin 1.3-1.4, Exposed 0.26.2-0.28.1

## Short-term solution

We disabled the `DataSourceHealthIndicator` and implemented our own whose
validation looks something like this

        var connection: Connection? = null
        return try {
            connection = dataSource.connection
            connection.isValid(timeout / 1000)
        } catch (exception: SQLException) {
            false
        } finally {
            connection?.close()
        }
