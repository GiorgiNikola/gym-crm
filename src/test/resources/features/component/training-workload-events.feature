@component
Feature: Trainings notify the workload service
  As gym-crm
  I want every added or withdrawn training to be announced to the workload service
  So that each trainer's monthly workload stays correct

  Background:
    Given a registered trainee named John Smith
    And a registered trainer named Sarah Miller

  @positive
  Scenario: Adding a training announces it once
    When the trainee logs a 60 minute training with the trainer on 1 August 2026
    Then the training is accepted
    And one workload event is published that adds 60 minutes on 1 August 2026 for the trainer

  @positive
  Scenario: Deleting a trainee withdraws each of their trainings
    Given the trainee logged these trainings with the trainer:
      | date              | minutes |
      | 1 August 2026     | 60      |
      | 10 September 2026 | 45      |
    When the trainee deletes their profile
    Then workload events are published that remove:
      | date              | minutes |
      | 1 August 2026     | 60      |
      | 10 September 2026 | 45      |

  @negative
  Scenario: A training with an unknown trainer is rejected and announces nothing
    When the trainee logs a training with a trainer who does not exist
    Then the request is rejected as not found
    And no workload event is published

  @negative
  Scenario: A training lasting zero minutes is rejected and announces nothing
    When the trainee logs a training lasting zero minutes
    Then the request is rejected as invalid
    And no workload event is published

  @negative
  Scenario: A trainee cannot log a training for someone else
    Given another registered trainee named Lucy Gray
    When the trainee logs a 60 minute training with the trainer for the other trainee
    Then the request is rejected as forbidden
    And no workload event is published

  @negative
  Scenario: A training is still saved while the message broker is down
    Given the message broker cannot be reached
    When the trainee logs a 60 minute training with the trainer on 1 August 2026
    Then the training is accepted
    And the trainee's training list shows a 60 minute training on 1 August 2026
    And no workload event is published
