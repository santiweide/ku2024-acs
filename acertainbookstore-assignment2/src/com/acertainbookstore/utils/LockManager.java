package com.acertainbookstore.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockManager {
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();
    private final ConcurrentHashMap<Integer, ReentrantReadWriteLock> rowLocks = new ConcurrentHashMap<>();

    public void acquireGlobalReadLock() {
        globalLock.readLock().lock();
    }

    public void releaseGlobalReadLock() {
        globalLock.readLock().unlock();
    }

    public void acquireGlobalWriteLock() {
        globalLock.writeLock().lock();
    }

    public void releaseGlobalWriteLock() {
        globalLock.writeLock().unlock();
    }

    public void acquireRowReadLock(int isbn) {
        getRowLock(isbn).readLock().lock();
    }

    public void releaseRowReadLock(int isbn) {
        getRowLock(isbn).readLock().unlock();
    }

    public void acquireRowWriteLock(int isbn) {
        getRowLock(isbn).writeLock().lock();
    }

    public void releaseRowWriteLock(int isbn) {
        getRowLock(isbn).writeLock().unlock();
    }

    private ReentrantReadWriteLock getRowLock(int isbn) {
        return rowLocks.computeIfAbsent(isbn, k -> new ReentrantReadWriteLock());
    }
}
