@integration
Feature: Trainees and trainers are served while parts of the system are down
  As a trainee or a trainer
  I want gym-crm to keep working when the workload service or messaging is unavailable
  So that an outage behind the scenes never loses my training or hangs my request

  Background:
    Given a registered trainee named Emma Stone
    And a registered trainer named Robert Brown

  @negative
  Scenario: A training logged while the workload service is down is counted once it is back
    Given the workload service is down
    And the trainee logged a 60 minute training with the trainer on 4 August 2026
    When the workload service is back
    Then the trainer sees 60 minutes in August 2026

  @negative
  Scenario: The trainer is told the workload is temporarily unavailable while the workload service is down
    Given the workload service is down
    When the trainer asks for their workload in August 2026
    Then the trainer is told the workload is temporarily unavailable

  @negative
  Scenario: An untrusted event put straight on the queue is set aside and changes no workload
    Given the trainee logged a 60 minute training with the trainer on 4 August 2026
    And the trainer sees 60 minutes in August 2026
    When someone puts a 90 minute training on 4 August 2026 for the trainer straight on the queue with a forged token
    Then the event is set aside as unauthenticated
    And the trainer still sees 60 minutes in August 2026

  @negative
  Scenario: A training logged while messaging is down is saved without keeping the trainee waiting
    Known limitation: there is no outbox yet. Once gym-crm gives up sending, the workload service never
    hears about this training, so the trainer's minutes do not include it even after messaging is back.

    Given messaging is down
    When the trainee logs a 60 minute training with the trainer on 4 August 2026
    Then the training is saved
    And the trainee waited less than 10 seconds
