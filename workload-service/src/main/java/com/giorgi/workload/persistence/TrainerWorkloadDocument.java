package com.giorgi.workload.persistence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(TrainerWorkloadDocument.COLLECTION)
public class TrainerWorkloadDocument {

    public static final String COLLECTION = "trainer_workloads";

    @Id
    private ObjectId id;

    @NotBlank
    private String username;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotNull
    private Boolean status;

    @NotNull
    private List<@Valid @NotNull YearDocument> years;

    @NotNull
    private List<@NotBlank String> recentEventIds;

    @Version
    private Long version;
}
