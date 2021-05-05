describe("Activity view", function() {
    before(() => {
        cy.dummyLogin("benjamin")
        cy.setup("task", {"project-name": "ACTIVITY TESTING",
            "activity": "preliminary-design"})
    })

    context("Edit Activity", function() {
        it("can be edited", function() {
            cy.get("@task").then((t) => cy.visit("#/projects/"+t["project-id"]))
            cy.wait(1000)
            cy.get("li a").contains("Eelprojekt").click({force: true})
            cy.get("button[data-cy=activity-edit-button]").click({force: true})
            cy.get("div[data-form-attribute=" +
                "\"[:activity/estimated-start-date :activity/estimated-end-date]\"]").should("exist")
        })
    })
})
