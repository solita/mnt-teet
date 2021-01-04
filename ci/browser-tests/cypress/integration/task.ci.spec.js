describe("Task view", function() {
    before(() => {

        cy.dummyLogin("Danny")

        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "TASK TESTING"}})
            .then((response) => {
                cy.wrap(response.body["task-url"]).as("taskURL")
            })
    })

    it("can edit task", function() {
        cy.visit(this.taskURL)
        cy.get("h1").contains("TASK TESTING")
        cy.get("div.task-page")
        cy.get(".task-header button").click()
        cy.get(".edit-task-form")
    })



})
