@component
Feature: Reading a trainer's workload
  As gym-crm
  I want to read how long a trainer trained per month
  So that I can show trainers their monthly workload

  Background:
    Given a trainer named Anna Kowalski
    And these trainings were recorded for the trainer:
      | date            | minutes |
      | 4 August 2026   | 60      |
      | 20 August 2026  | 30      |
      | 10 January 2027 | 45      |

  @positive
  Scenario: The monthly total of a trainer
    When gym-crm asks for the trainer's workload in August 2026
    Then the monthly total is 90 minutes

  @positive
  Scenario: A month without trainings has no training time
    When gym-crm asks for the trainer's workload in September 2026
    Then the monthly total is 0 minutes

  @positive
  Scenario: The full summary of a trainer
    When gym-crm asks for the trainer's workload summary
    Then the summary shows the trainer as active with these months:
      | month        | minutes |
      | August 2026  | 90      |
      | January 2027 | 45      |

  @negative
  Scenario Outline: The workload of an unknown trainer is not found
    When gym-crm asks for the <view> of a trainer who never trained
    Then the request is rejected as not found

    Examples:
      | view             |
      | monthly workload |
      | workload summary |

  @negative
  Scenario Outline: A request without a token is rejected
    When gym-crm asks for the trainer's <view> without a token
    Then the request is rejected as unauthenticated

    Examples:
      | view             |
      | monthly workload |
      | workload summary |

  @negative
  Scenario Outline: A token that may only record workloads cannot read them
    When gym-crm asks for the trainer's <view> with a token that may only record workloads
    Then the request is rejected as forbidden

    Examples:
      | view             |
      | monthly workload |
      | workload summary |

  @negative
  Scenario: A month outside the calendar is rejected
    When gym-crm asks for the trainer's workload in month 13 of 2026
    Then the request is rejected as invalid
