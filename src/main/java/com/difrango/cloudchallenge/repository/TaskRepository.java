/* (C)2022 */
package com.difrango.cloudchallenge.repository;

import com.difrango.cloudchallenge.model.Task;
import org.springframework.data.repository.CrudRepository;

public interface TaskRepository extends CrudRepository<Task, Long> {
}
