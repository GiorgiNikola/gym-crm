@component
Feature: Rejected training events are set aside
  As the operator of the workload service
  I want events that cannot be trusted or understood to be dead-lettered with a reason
  So that they change no workload and can be inspected later

  Background:
    Given a trainer named Tom Becker

  @negative
  Scenario Outline: An event without valid credentials is dead-lettered
    When gym-crm sends a training event <credentials>
    Then the event is dead-lettered as <reason>
    And no trainer workload is stored

    Examples:
      | credentials                               | reason          |
      | without a service token                   | unauthenticated |
      | with an expired service token             | unauthenticated |
      | with a token that may only read workloads | forbidden       |

  @negative
  Scenario Outline: An event that cannot be read is dead-lettered as malformed
    When gym-crm sends a training event <problem>
    Then the event is dead-lettered as malformed
    And no trainer workload is stored

    Examples:
      | problem                          |
      | with an unreadable body          |
      | of an unknown event type         |
      | in an unsupported event version  |
      | with a training date in month 13 |

  @negative
  Scenario Outline: An event with invalid training details is dead-lettered as invalid
    When gym-crm sends a training event <problem>
    Then the event is dead-lettered as invalid
    And no trainer workload is stored

    Examples:
      | problem                           |
      | with a blank trainer username     |
      | with a negative training duration |
