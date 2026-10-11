package com.wbscouting.api.entity;

import com.wbscouting.api.enums.MediaType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "model_media", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 30, nullable = false)
    private MediaType mediaType;

    @Column(name = "file_url", columnDefinition = "TEXT", nullable = false)
    private String fileUrl;

    @Column(name = "file_path", columnDefinition = "TEXT", nullable = false)
    private String filePath;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_cover", nullable = false)
    @Builder.Default
    private Boolean isCover = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ============================================================
    // 🔥 GETTERS/SETTERS EXPLICITOS
    // ============================================================
    public UUID getId() { return this.id; }
    public void setId(UUID id) { this.id = id; }

    public Model getModel() { return this.model; }
    public void setModel(Model model) { this.model = model; }

    public MediaType getMediaType() { return this.mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    public void setMediaType(String mediaTypeStr) {
        if (mediaTypeStr != null) {
            try {
                this.mediaType = MediaType.valueOf(mediaTypeStr.trim().toUpperCase());
            } catch (Exception e) {
                this.mediaType = MediaType.BOOK;
            }
        }
    }

    public String getFileUrl() { return this.fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getMediaUrl() { return this.fileUrl; }
    public void setMediaUrl(String mediaUrl) { this.fileUrl = mediaUrl; }

    public String getFilePath() { return this.filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getDisplayOrder() { return this.displayOrder != null ? this.displayOrder : 0; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public Boolean getIsCover() { return Boolean.TRUE.equals(this.isCover); }
    public void setIsCover(Boolean isCover) { this.isCover = isCover != null ? isCover : false; }

    public Boolean getIsActive() { return this.isActive != null ? this.isActive : true; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive != null ? isActive : true; }

    public OffsetDateTime getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return this.updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

