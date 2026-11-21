@component
Feature: Trainer workload follows training events
  As gym-crm
  I want every added or cancelled training to change the trainer's monthly workload
  So that the monthly training time of each trainer is always up to date

  Background:
    Given a trainer named Sarah Miller

  @positive
  Scenario: The first training creates the trainer's month
    When a 60 minute training on 4 August 2026 is added for the trainer
    Then the trainer's workload is:
      | month       | minutes |
      | August 2026 | 60      |

  @positive
  Scenario: A second training in the same month adds up
    Given a 60 minute training on 4 August 2026 was added for the trainer
    When a 45 minute training on 20 August 2026 is added for the trainer
    Then the trainer has 105 minutes in August 2026

  @positive
  Scenario: Trainings in a new month and a new year are kept apart
    Given a 60 minute training on 4 August 2026 was added for the trainer
    When a 30 minute training on 3 September 2026 is added for the trainer
    And a 15 minute training on 10 January 2027 is added for the trainer
    Then the trainer's workload is:
      | month          | minutes |
      | August 2026    | 60      |
      | September 2026 | 30      |
      | January 2027   | 15      |

  @positive
  Scenario Outline: A cancelled training reduces the month but never below zero
    Given a 60 minute training on 4 August 2026 was added for the trainer
    When a <cancelled> minute training on 4 August 2026 is cancelled for the trainer
    Then the trainer has <left> minutes in August 2026

    Examples:
      | cancelled | left |
      | 45        | 15   |
      | 60        | 0    |
      | 90        | 0    |

  @positive
  Scenario: An inactive trainer is stored as inactive
    Given the trainer is inactive
    When a 60 minute training on 4 August 2026 is added for the trainer
    Then the trainer is stored as inactive

  @positive
  Scenario: The same event delivered twice is counted once
    When the same 60 minute training on 4 August 2026 is delivered twice
    Then the trainer has 60 minutes in August 2026

  @positive
  Scenario: A training is still counted when the storage fails on the first delivery
    Given a 60 minute training on 4 August 2026 was added for the trainer
    And the workload storage fails on the next save for the trainer
    When a 30 minute training on 20 August 2026 is added for the trainer
    Then the trainer has 90 minutes in August 2026
    And the storage saved the training on the second attempt
