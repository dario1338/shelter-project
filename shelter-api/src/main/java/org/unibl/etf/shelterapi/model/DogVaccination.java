package org.unibl.etf.shelterapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "dog_vaccinations")
public class DogVaccination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dog_id", nullable = false)
    private DogCare care;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "vaccinated_on", nullable = false)
    private LocalDate vaccinatedOn;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    protected DogVaccination() {
        // required by JPA
    }

    public DogVaccination(DogCare care, String name, LocalDate vaccinatedOn, LocalDate nextDueOn) {
        this.care = care;
        this.name = name;
        this.vaccinatedOn = vaccinatedOn;
        this.nextDueOn = nextDueOn;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getVaccinatedOn() {
        return vaccinatedOn;
    }

    public LocalDate getNextDueOn() {
        return nextDueOn;
    }
}
