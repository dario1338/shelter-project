package org.unibl.etf.shelterapi.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Care details of one dog. The id is the id of the dog: a dog has at most one set of care details. */
@Entity
@Table(name = "dog_care")
public class DogCare {

    @Id
    @Column(name = "dog_id")
    private Long dogId;

    private Boolean sterilized;

    @Column(name = "microchip_number", length = 30)
    private String microchipNumber;

    @Column(name = "health_notes", columnDefinition = "TEXT")
    private String healthNotes;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @OneToMany(mappedBy = "care", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("vaccinatedOn DESC, id ASC")
    private List<DogVaccination> vaccinations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DogCare() {
        // required by JPA
    }

    public DogCare(Long dogId) {
        this.dogId = dogId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces everything. The update time is set here, because a change of the list alone does not touch the row. */
    public void replaceWith(Boolean sterilized, String microchipNumber, String healthNotes, String internalNote,
                            List<DogVaccination> newVaccinations) {
        this.sterilized = sterilized;
        this.microchipNumber = microchipNumber;
        this.healthNotes = healthNotes;
        this.internalNote = internalNote;
        this.vaccinations.clear();
        this.vaccinations.addAll(newVaccinations);
        this.updatedAt = Instant.now();
    }

    public Long getDogId() {
        return dogId;
    }

    public Boolean getSterilized() {
        return sterilized;
    }

    public String getMicrochipNumber() {
        return microchipNumber;
    }

    public String getHealthNotes() {
        return healthNotes;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public List<DogVaccination> getVaccinations() {
        return vaccinations;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
