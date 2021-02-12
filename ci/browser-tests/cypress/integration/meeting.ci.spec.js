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

    function addTopic(title) {
        cy.get("#add-agenda").click()
        cy.formInput(":meeting.agenda/topic", title)
        cy.get("[data-cy='meeting-details'] [type=submit]").click()
        cy.get(".MuiSnackbar-root").should("exist")
    }
    function createMeetingWithTopic() {


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

        addTopic("test topic")
        cy.get("div.agenda-heading").contains("test topic")

    }

    it("Creates meeting with topic", function() {
        createMeetingWithTopic.call(this)
    })

    it("remembers when user has seen meeting", function() {
        // Create meeting with Danny
        createMeetingWithTopic.call(this)

        // Add carla as reviewer
        cy.get(".new-participant input").type("carla")
        cy.get(".select-user-entry").contains("Carla Consultant").click()

        cy.selectByKeyword(".new-participant select", ":participation.role/reviewer")
        cy.get(".new-participant button[type=submit]").click()
        cy.wait(500)
        cy.get(".participant-list").contains("Carla Consultant")

        cy.location().as("meeting")

        // Login as Carla
        cy.dummyLogin("Carla")
        cy.get("@meeting").then((l) => cy.visit(l.toString()))

        // Add topic as Carla
        addTopic("Carla's new topic")
        cy.wait(500)
        cy.get("div.agenda-heading").contains("Carla's new topic")

        // Go back to meeting with Danny and see new topic is highlighted
        cy.dummyLogin("Danny")
        cy.get("@meeting").then((l) => cy.visit(l.toString()))

        cy.get(".new-indicator")

    })

})
