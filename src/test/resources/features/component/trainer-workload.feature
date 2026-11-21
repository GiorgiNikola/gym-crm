@component
Feature: A trainer's monthly workload
  As a trainer
  I want to see how many minutes I trained in a month
  So that I can follow my workload

  Background:
    Given a registered trainer named Sarah Miller

  @positive
  Scenario: The trainer sees the minutes the workload service reports
    Given the workload service reports 150 minutes for the trainer in August 2026
    When the trainer asks for their workload in August 2026
    Then the trainer sees 150 minutes for August 2026
    And the workload service was asked with gym-crm's read token and the request's transaction id

  @positive
  Scenario: A trainer the workload service does not know yet has no minutes
    Given the workload service does not know the trainer
    When the trainer asks for their workload in August 2026
    Then the trainer sees 0 minutes for August 2026

  @negative
  Scenario Outline: The trainer is told the workload is temporarily unavailable when the workload service <trouble>
    Given the workload service <trouble>
    When the trainer asks for their workload in August 2026
    Then the trainer is told the workload is temporarily unavailable

    Examples:
      | trouble                   |
      | is down                   |
      | drops the connection      |
      | fails with a server error |
      | answers too slowly        |

  @negative
  Scenario: Repeated failures stop the calls to the workload service
    Given the workload service is down
    When the trainer asks for their workload in August 2026 6 times
    Then every answer says the workload is temporarily unavailable
    And the workload service was called only 5 times

  @negative
  Scenario Outline: Only the trainer can read their own workload
    Given another registered <role> named Robert Brown
    When the other <role> asks for the trainer's workload in August 2026
    Then the request is rejected as forbidden
    And the workload service was not called

    Examples:
      | role    |
      | trainee |
      | trainer |
