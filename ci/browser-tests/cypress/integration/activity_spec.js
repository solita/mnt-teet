describe("Activity view", function() {
    before(() => {
        cy.dummyLogin("Carla")
        cy.selectLanguage("#EN")
        cy.projectByName("integration test project")
        cy.get("li a").contains("Detailed design").click()
    })

    context("Edit Activity", function() {
        it("can be edited", function() {
            cy.get("button[data-cy=activity-edit-button]").click({force: true})
            cy.get("div[data-form-attribute=" +
                "\"[:activity/estimated-start-date :activity/estimated-end-date]\"]").should("exist")
        })
    })
})
