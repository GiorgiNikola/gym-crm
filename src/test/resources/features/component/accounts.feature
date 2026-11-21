@component
Feature: Accounts
  As a trainee or a trainer
  I want to register and log in
  So that I can use the gym with my own account

  @positive
  Scenario Outline: A new <role> registers and logs in with the issued credentials
    When a new <role> named <first name> <last name> registers
    Then the <role> receives a username made of their name and a password
    And the <role> can log in with those credentials

    Examples:
      | role    | first name | last name |
      | trainee | John       | Smith     |
      | trainer | Sarah      | Miller    |

  @negative
  Scenario: A wrong password is rejected
    Given a registered trainee named Emma Stone
    When the trainee logs in with a wrong password
    Then the request is rejected as unauthenticated

  @negative
  Scenario: Three failed logins lock the account
    Given a registered trainee named Emma Stone
    And the trainee failed to log in 3 times
    When the trainee logs in with the right password
    Then the request is rejected as locked

  @negative
  Scenario Outline: A registration with missing or invalid details is rejected
    When a new <role> registers with <problem>
    Then the request is rejected as invalid
    And no account is created

    Examples:
      | role    | problem                                |
      | trainee | no first name                          |
      | trainee | no last name                           |
      | trainee | a first name longer than 50 characters |
      | trainee | a birth date in the future             |
      | trainer | no specialization                      |
      | trainer | an unknown specialization              |
