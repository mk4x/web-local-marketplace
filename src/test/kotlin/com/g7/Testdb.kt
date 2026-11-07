package com.g7

import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListenerProvider
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.BeforeTest

object TestDb {
    private val container =
        PostgreSQLContainer(
            DockerImageName
<<<<<<< HEAD
                .parse("postgis/postgis:16-3.7-alpine")
||||||| parent of e23fa9d (add integration test)
=======
                .parse("postgis/postgis:16-3.5-alpine")
>>>>>>> e23fa9d (add integration test)
                .asCompatibleSubstituteFor("postgres"),
        ).apply { start() }

    private val dataSource =
        PGSimpleDataSource().apply {
            setUrl(container.jdbcUrl)
            user = container.username
            password = container.password
        }

    val dsl: DSLContext =
        DSL.using(
            dataSource,
            SQLDialect.POSTGRES,
            Settings().withStatementType(StatementType.PREPARED_STATEMENT),
        )

    init {
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    fun reset() {
        dsl.execute(
            """
            truncate listings_current, listing_versions, listings,
                     user_versions, users, categories cascade
            """,
        )
        dsl.execute(
            """
            insert into categories (path, label) values
                ('vehicles', 'Vehicles'),
                ('vehicles.bikes', 'Bikes'),
                ('home', 'Home')
            """,
        )
    }
}

abstract class DbTest {
    protected val db: DSLContext get() = TestDb.dsl

    @BeforeTest
    fun resetDb() = TestDb.reset()

    protected fun executions(block: DSLContext.() -> Unit): Int {
        var count = 0
        val counter =
            object : ExecuteListener {
                override fun executeStart(ctx: ExecuteContext) {
                    count++
                }
            }
        db
            .configuration()
            .derive(DefaultExecuteListenerProvider(counter))
            .dsl()
            .block()
        return count
    }
}
