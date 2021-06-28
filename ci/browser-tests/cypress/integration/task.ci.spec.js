describe("Task view", function() {
    before(() => {

        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "TASK TESTING"}})
            .then((response) => {
                cy.wrap(response.body["task-url"]).as("taskURL")
            })
    })

    it("carla can't delete task", function() {
        cy.dummyLogin("Carla")
        cy.visit(this.taskURL)
        cy.get("[data-cy='project-header']").contains("TASK TESTING")
        cy.get("div.task-page")
        cy.get(".task-header button").should("not.exist")
    })

    it("Danny can edit task", function() {
        cy.dummyLogin("Danny")
        cy.visit(this.taskURL)
        cy.get("[data-cy='project-header']").contains("TASK TESTING")
        cy.get("div.task-page")
        cy.get(".task-header button").click()
        cy.get(".edit-task-form")
        cy.get("#delete-button").click()
        cy.get("#confirm-delete").click()

        // redirect to activity page
        cy.get(".activity-content")

    })

})
