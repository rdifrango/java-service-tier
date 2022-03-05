/* (C)2022 */
package com.difrango.cloudchallenge.repository;

import com.difrango.cloudchallenge.model.Person;
import org.springframework.data.repository.CrudRepository;

public interface PersonRepository extends CrudRepository<Person, Long> {
}
