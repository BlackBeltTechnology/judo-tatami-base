package hu.blackbelt.judo.tatami.rdbms2liquibase;

public interface ReviewResolver {
    boolean exists(String name);
    String resolve(String name);
}
