package com.wbscouting.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidate_submissions", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSubmission implements Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "protocol", length = 50, nullable = false, unique = true)
    private String protocol;

    @Column(name = "full_name", length = 120, nullable = false)
    private String fullName;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "phone", length = 50, nullable = false)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "gender", length = 20, nullable = false)
    private String gender;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "state", length = 50, nullable = false)
    private String state;

    @Column(name = "height", precision = 4, scale = 2, nullable = false)
    private BigDecimal height;

    @Column(name = "bust", precision = 5, scale = 2)
    private BigDecimal bust;

    @Column(name = "waist", precision = 5, scale = 2)
    private BigDecimal waist;

    @Column(name = "hips", precision = 5, scale = 2)
    private BigDecimal hips;

    @Column(name = "shoe_size")
    private Integer shoeSize;

    @Column(name = "eye_color", length = 50)
    private String eyeColor;

    @Column(name = "hair_color", length = 50)
    private String hairColor;

    @Column(name = "instagram_handle", length = 100)
    private String instagramHandle;

    @Column(name = "guardian_name", length = 120)
    private String guardianName;

    @Column(name = "guardian_phone", length = 50)
    private String guardianPhone;

    @Column(name = "guardian_email", length = 100)
    private String guardianEmail;

    @Column(name = "face_photo_url", columnDefinition = "TEXT", nullable = false)
    private String facePhotoUrl;

    @Column(name = "profile_photo_url", columnDefinition = "TEXT", nullable = false)
    private String profilePhotoUrl;

    @Column(name = "full_body_photo_url", columnDefinition = "TEXT", nullable = false)
    private String fullBodyPhotoUrl;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "feedback_notes", length = 500)
    private String feedbackNotes;

    @Column(name = "reviewed_by", length = 150)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "converted_to_model_id")
    private UUID convertedToModelId;

    @Column(name = "lgpd_consent", nullable = false)
    @Builder.Default
    private Boolean lgpdConsent = false;

    @Column(name = "lgpd_consent_at")
    private OffsetDateTime lgpdConsentAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CandidatePhoto> photos = new ArrayList<>();

    @Transient
    @Builder.Default
    private boolean isNewRecord = true;

    @Override
    public boolean isNew() {
        return this.isNewRecord || this.id == null;
    }

    @PostPersist
    @PostLoad
    public void markNotNew() {
        this.isNewRecord = false;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
        }
        if (this.lgpdConsent == null) {
            this.lgpdConsent = true;
        }
    }

    public void addPhoto(CandidatePhoto photo) {
        if (this.photos == null) {
            this.photos = new ArrayList<>();
        }
        this.photos.add(photo);
        photo.setSubmission(this);
    }
}
