package com.hospital.customer_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUsernameIgnoreCase(String username);
    Optional<Patient> findByEmailIgnoreCase(String email);
}
