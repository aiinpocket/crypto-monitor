package com.aiinpocket.btctrade.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 模板投票 Entity。
 * 每位用戶對每個社群模板只能投一票（讚或噓），可更改投票方向。
 */
@Entity
@Table(name = "template_vote",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vote_user_template",
                        columnNames = {"user_id", "community_template_id"})
        },
        indexes = {
                @Index(name = "idx_vote_template", columnList = "community_template_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 投票者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private AppUser user;

    /** 被投票的社群模板 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_template_id", nullable = false)
    @JsonIgnore
    private CommunityTemplate communityTemplate;

    /** true=讚, false=噓 */
    @Column(nullable = false)
    private boolean thumbsUp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
