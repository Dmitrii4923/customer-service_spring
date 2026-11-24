package com.hospital.customer_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/customers")
public class PatientController {
    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientRepository repository;
    private final PolicyRepository policyRepository;

    public PatientController(PatientRepository repository, PolicyRepository policyRepository) {
        this.repository = repository;
        this.policyRepository = policyRepository;
    }

    @GetMapping
    public List<Patient> getAll() {
        log.debug("Запрошен список пациентов");
        return repository.findAll();
    }

    @PostMapping
    public Patient create(@RequestBody Patient patient) {
        log.info("Создание пациента: username={}", patient.getUsername());
        Patient saved = repository.save(patient);
        log.info("Пациент создан: id={}", saved.getId());
        return saved;
    }

    @PostMapping("/register")
    public Patient register(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) Patient body) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");

        log.info("Попытка регистрации профиля: username={}, email={}", username, email);

        if (username == null || username.isBlank()) {
            log.warn("В токене отсутствует preferred_username");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В токене нет логина (preferred_username)");
        }

        Optional<Patient> existing = repository.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            log.info("Профиль уже существует: id={}", existing.get().getId());
            return existing.get();
        }

        Patient patient = new Patient();
        patient.setUsername(username);
        patient.setEmail(email);
        patient.setName(body != null && body.getName() != null ? body.getName() : username);

        Patient saved = repository.save(patient);
        log.info("Создан новый профиль пациента: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    @GetMapping("/me")
    public Patient me(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        log.debug("Запрошен профиль пациента: username={}", username);

        Optional<Patient> patient = username == null
                ? Optional.empty()
                : repository.findByUsernameIgnoreCase(username);

        if (patient.isEmpty() && email != null) {
            patient = repository.findByEmailIgnoreCase(email);
        }

        return patient.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Профиль пациента не привязан к учётной записи"));
    }

    @GetMapping("/{id}")
    public Patient getById(@PathVariable Long id) {
        log.debug("Запрошен пациент: id={}", id);
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Пациент не найден"));
    }

    @GetMapping("/me/policies")
    public List<Policy> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = currentPatient(jwt);
        log.debug("Запрошены полисы пациента: id={}", patient.getId());
        return policyRepository.findByPatientIdOrderByIdDesc(patient.getId());
    }

    @PostMapping("/me/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public Policy createPolicy(@AuthenticationPrincipal Jwt jwt, @RequestBody Policy body) {
        Patient patient = currentPatient(jwt);
        if (body == null || body.getNumber() == null || body.getNumber().replaceAll("\\D", "").length() < 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите корректный номер полиса");
        }
        Policy policy = new Policy();
        policy.setNumber(body.getNumber().trim());
        policy.setDate(body.getDate());
        policy.setStatus(body.getStatus() == null || body.getStatus().isBlank() ? "Действует" : body.getStatus());
        policy.setPatient(patient);
        Policy saved = policyRepository.save(policy);
        log.info("Добавлен полис: id={}, patientId={}", saved.getId(), patient.getId());
        return saved;
    }

    @DeleteMapping("/me/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Patient patient = currentPatient(jwt);
        Policy policy = policyRepository.findByIdAndPatientId(id, patient.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Полис не найден"));
        policyRepository.delete(policy);
        log.info("Удалён полис: id={}, patientId={}", id, patient.getId());
    }

    private Patient currentPatient(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");

        Optional<Patient> patient = username == null
                ? Optional.empty()
                : repository.findByUsernameIgnoreCase(username);

        if (patient.isEmpty() && email != null) {
            patient = repository.findByEmailIgnoreCase(email);
        }

        return patient.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Профиль пациента не привязан к учётной записи"));
    }
}