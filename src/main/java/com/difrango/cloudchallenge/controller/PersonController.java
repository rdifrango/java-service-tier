/* (C)2022 */
package com.difrango.cloudchallenge.controller;

import com.difrango.cloudchallenge.model.Person;
import com.difrango.cloudchallenge.model.Task;
import com.difrango.cloudchallenge.repository.PersonRepository;
import com.difrango.cloudchallenge.repository.TaskRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
public class PersonController {
    private final PersonRepository personRepository;

    private final TaskRepository taskRepository;

    public PersonController(PersonRepository personRepository, TaskRepository taskRepository) {
        this.personRepository = personRepository;
        this.taskRepository = taskRepository;
    }

    @RequestMapping(value = "/People", method = RequestMethod.GET)
    @ResponseBody
    public Iterable<Person> getPeople() {
        return personRepository.findAll();
    }

    @RequestMapping(
            value = "/People",
            method = RequestMethod.POST,
            headers = {"Content-type=application/json"})
    @ResponseBody
    public Person addPerson(@RequestBody Person person) {
        return personRepository.save(person);
    }

    @RequestMapping(value = "/People/{id}", method = RequestMethod.DELETE)
    public @ResponseBody
    void removePerson(@PathVariable("id") long id) {
        personRepository.findById(id).ifPresent(entity -> personRepository.delete(entity));
    }

    @RequestMapping(value = "/People/{id}/tasks", method = RequestMethod.DELETE)
    public @ResponseBody
    void removePersonTasks(@PathVariable("id") long id) {
        personRepository
                .findById(id)
                .ifPresent(
                        entity -> {
                            entity.setTasks(Collections.emptyList());
                            personRepository.save(entity);
                        });
    }

    @RequestMapping(value = "/People/{id}/tasks", method = RequestMethod.GET)
    public @ResponseBody
    Iterable<Task> getPersonTasks(@PathVariable("id") long id) {
        return personRepository.findById(id).orElse(new Person()).getTasks();
    }

    @RequestMapping(
            value = "/People/{id}/tasks",
            method = RequestMethod.POST,
            headers = {"Content-type=application/json"})
    @ResponseBody
    public Task addPersonTask(@PathVariable("id") long id, @RequestBody Task task) {
        personRepository.findById(id).ifPresent(person -> task.setPerson(person));
        return taskRepository.save(task);
    }

    @RequestMapping(
            value = "/People/{id}/task",
            method = RequestMethod.POST,
            headers = {"Content-type=application/json"})
    public @ResponseBody
    Task addTask(@PathVariable("id") long id, @RequestBody Task task) {
        return taskRepository.save(task);
    }
}
