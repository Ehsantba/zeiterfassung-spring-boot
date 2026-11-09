package de.zeiterfassung.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supervisor_id")
    private AppUser supervisor;

    @Column(nullable = false)
    private boolean active = true;

    protected AppUser() {
    }

    public AppUser(String name, String email, String passwordHash, Role role, AppUser supervisor) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.supervisor = supervisor;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public AppUser getSupervisor() { return supervisor; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
