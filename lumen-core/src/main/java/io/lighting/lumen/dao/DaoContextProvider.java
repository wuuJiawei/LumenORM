package io.lighting.lumen.dao;

/**
 * Internal hook for supplying {@link DaoContext} to DAO default methods.
 */
public interface DaoContextProvider {
    DaoContext daoContext();
}
