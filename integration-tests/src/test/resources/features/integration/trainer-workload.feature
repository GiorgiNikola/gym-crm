@integration
Feature: Trainers see the workload of the trainings logged with them
  As a trainer
  I want the trainings my trainees log to show up as my monthly workload
  So that I can follow how much I train

  Background:
    Given a registered trainee named John Smith
    And a registered trainer named Sarah Miller

  @positive
  Scenario: A logged training shows up as the trainer's minutes in that month
    When the trainee logs a 60 minute training with the trainer on 4 August 2026
    Then the trainer sees 60 minutes in August 2026

  @positive
  Scenario: Two trainings in one month add up
    When the trainee logs a 60 minute training with the trainer on 4 August 2026
    And the trainee logs a 45 minute training with the trainer on 20 August 2026
    Then the trainer sees 105 minutes in August 2026

  @positive
  Scenario: Trainings in different months stay separate
    When the trainee logs a 60 minute training with the trainer on 4 August 2026
    And the trainee logs a 30 minute training with the trainer on 3 September 2026
    Then the trainer sees 60 minutes in August 2026
    And the trainer sees 30 minutes in September 2026

  @positive
  Scenario: Deleting the trainee removes the trainer's minutes
    Given the trainee logged a 60 minute training with the trainer on 4 August 2026
    And the trainer sees 60 minutes in August 2026
    When the trainee deletes their profile
    Then the trainer sees 0 minutes in August 2026

  @positive
  Scenario: The trainer's workload is still there after the workload service restarts
    Given the trainee logged a 60 minute training with the trainer on 4 August 2026
    And the trainer sees 60 minutes in August 2026
    When the workload service restarts
    Then the trainer still sees 60 minutes in August 2026
