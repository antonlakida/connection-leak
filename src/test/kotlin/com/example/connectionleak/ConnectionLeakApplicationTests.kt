package com.example.connectionleak

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.spring.SpringTransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@SpringBootTest
@ContextConfiguration(classes = [ConnectionLeakApplicationTests.Config::class])
@TestPropertySource(properties = ["logging.level.root=DEBUG"])
class ConnectionLeakApplicationTests {

    @Autowired
    private lateinit var dataSource: HikariDataSource

    @Autowired
    private lateinit var healthIndicator: DataSourceHealthIndicator

    @Test
    fun `does not return connections to the pool when health check called after transaction`() {
        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE

        val latch = CountDownLatch(POOL_SIZE)

        repeat(POOL_SIZE) {
            thread(start = true) {
                transaction { }

                healthIndicator.health()

                latch.countDown()
            }
        }

        latch.await()

        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE
    }

    @Test
    fun `returns all connections to the pool when only health check is called`() {
        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE

        val latch = CountDownLatch(POOL_SIZE)

        repeat(POOL_SIZE) {
            thread(start = true) {
                healthIndicator.health()

                latch.countDown()
            }
        }

        latch.await()

        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE
    }

    @Test
    fun `returns all connections to the pool when only transaction is called`() {
        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE

        val latch = CountDownLatch(POOL_SIZE)

        repeat(POOL_SIZE) {
            thread(start = true) {
                transaction { }

                latch.countDown()
            }
        }

        latch.await()

        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE
    }

    @Test
    fun `returns all connections to the pool when health check called before transaction`() {
        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE

        val latch = CountDownLatch(POOL_SIZE)

        repeat(POOL_SIZE) {
            thread(start = true) {
                healthIndicator.health()

                transaction { }

                latch.countDown()
            }
        }

        latch.await()

        dataSource.hikariPoolMXBean.idleConnections shouldBe POOL_SIZE
    }

    @Configuration
    class Config {

        @Bean
        fun hikariConfig() = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:~/test"
            username = "sa"
            password = "sa"
            maximumPoolSize = POOL_SIZE
        }

        @Bean
        fun dataSource() = HikariDataSource(hikariConfig())

        @Bean
        fun dataSourceHealthIndicator() = DataSourceHealthIndicator(dataSource())

        @Bean
        fun transactionManager() = SpringTransactionManager(dataSource())
    }

    companion object {

        private const val POOL_SIZE = 10
    }
}
