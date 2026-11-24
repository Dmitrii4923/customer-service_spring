package com.hospital.customer_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByPatientIdOrderByIdDesc(Long patientId);
    Optional<Policy> findByIdAndPatientId(Long id, Long patientId);
}
