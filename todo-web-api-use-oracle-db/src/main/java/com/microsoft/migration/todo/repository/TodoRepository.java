package com.microsoft.migration.todo.repository;

import com.microsoft.migration.todo.model.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<TodoItem, Long> {

    // Custom query methods
    List<TodoItem> findByCompleted(boolean completed);

    List<TodoItem> findByPriorityGreaterThanEqual(int priority);

    // Oracle specific SQL with VARCHAR2
    @Query(value = "SELECT * FROM TODO_ITEMS WHERE TITLE LIKE '%' || :keyword || '%' OR DESCRIPTION LIKE '%' || :keyword || '%'",
           nativeQuery = true)
    List<TodoItem> findByKeyword(String keyword);

    // Another Oracle specific query showing off more Oracle SQL features
    @Query(value = "SELECT * FROM TODO_ITEMS WHERE PRIORITY > :priority AND ROWNUM <= :limit ORDER BY CREATED_AT DESC",
           nativeQuery = true)
    List<TodoItem> findTopPriorityTasks(int priority, int limit);

    @Query(value = "SELECT * FROM TODO_ITEMS " +
                   "WHERE DUE_DATE < SYSDATE " +
                   "AND COMPLETED = 0 " +
                   "ORDER BY PRIORITY DESC, DUE_DATE ASC",
           nativeQuery = true)
    List<TodoItem> findOverdue();

    @Modifying
    @Query(value = "UPDATE TODO_ITEMS " +
                   "SET PRIORITY = :newPriority, " +
                   "UPDATED_AT = SYSTIMESTAMP " +
                   "WHERE DUE_DATE < :cutoffDate " +
                   "AND COMPLETED = 0",
           nativeQuery = true)
    int bumpPriorityBefore(@Param("cutoffDate") LocalDateTime cutoffDate, @Param("newPriority") int newPriority);

    @Query(value = "SELECT * FROM TODO_ITEMS " +
                   "WHERE DBMS_LOB.INSTR(TITLE, :searchTerm) > 0 " +
                   "OR DBMS_LOB.INSTR(DESCRIPTION, :searchTerm) > 0",
           nativeQuery = true)
    List<TodoItem> searchVarchar2(@Param("searchTerm") String searchTerm);
}
