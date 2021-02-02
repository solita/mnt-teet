context("Meetings", () => {
    beforeEach(() => {
        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "MEETING TESTING"}})
            .then((response) => {
                cy.wrap(response.body["project-id"]).as("projectID")
            })

        cy.dummyLogin("Danny")
    })

    it("Creates meeting with topic", function() {
        cy.visit(`#/projects/${this.projectID}/meetings`)
        cy.get("[data-cy='activity-link:activity.name/pre-design']").click()
        cy.get(".project-navigator-add-meeting").click()
        cy.get(`input[class*=':date-input']`).type(new Date().toLocaleDateString("et-EE"))
        cy.get("[class*=start-time]").type("10:00")
        cy.get("[class*=end-time]").type("11:00")

        cy.formInput(
            ":meeting/title", "test meeting",
            ":meeting/location", "CI tests")

        cy.formSubmit()

        cy.get("#add-agenda").click()

        cy.formInput(":meeting.agenda/topic", "test topic")
        cy.get("[data-cy='meeting-details'] [type=submit]").click()

        cy.get("div.agenda-heading").contains("test topic")


    })

})
