describe("Activity view", function() {
    beforeEach(() => {

        cy.request({method: "POST",
            url: "/testsetup/task",
            body: {"project-name": "ACTIVITY TESTING"}})
            .then((response) => {
                cy.wrap(response.body["project-id"]).as("projectID")
            })

        cy.dummyLogin("Danny")
    })

    context("Edit Activity", function() {
        it("can be edited", function() {
            // visit project page
            cy.visit("#/projects/"+this.projectID)
            cy.get("li a").contains("Eskiis").click({force: true})
            cy.get("button[data-cy=activity-edit-button]").click({force: true})
            cy.get("div[data-form-attribute=" +
                "\"[:activity/estimated-start-date :activity/estimated-end-date]\"]").should("exist")
        })
    })
})
