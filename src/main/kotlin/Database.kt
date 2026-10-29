import org.flywaydb.core.Flyway

object Database {
    fun migrate(
        url: String,
        user: String,
        password: String,
    ) {
        Flyway
            .configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
