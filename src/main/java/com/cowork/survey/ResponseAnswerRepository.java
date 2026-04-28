package com.cowork.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResponseAnswerRepository extends JpaRepository<ResponseAnswer, Long> {

    List<ResponseAnswer> findByResponseIdIn(Collection<Long> responseIds);
}
