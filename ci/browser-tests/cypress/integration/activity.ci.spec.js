describe("Activity view", function() {
    beforeEach(() => {
        cy.setup("task", {"project-name": "ACTIVITY TESTING", "activity": "preliminary-design"})
        cy.dummyLogin("Danny")
    })

    context("Edit Activity", function() {
        it("can be edited", function() {
            // visit project page
            cy.get("@task").then((t) => cy.visit("#/projects/"+t["project-id"]))
            cy.get("li a").contains("Eelprojekt").click({force: true})
            cy.get("button[data-cy=activity-edit-button]").click({force: true})
            cy.get("div[data-form-attribute=" +
                "\"[:activity/estimated-start-date :activity/estimated-end-date]\"]").should("exist")
        })
    })
})
