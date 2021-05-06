describe("Activity view", function() {
    beforeEach(() => {
        cy.dummyLogin("Danny")
    })

    function selectProject() {
        cy.get("span").contains("menu").click({force: true});

        cy.get("span").contains("Projektide loetelu").click();
        cy.contains("Kõik projektid").click();

        cy.get("#quick-search").type("integration test project");
        cy.get("div").contains("integration test project").click();
    }

    function selectActivity() {
        cy.get("li a").contains("Põhiprojekt").click({force: true})
    }

    context("Edit Activity", function() {
        it("can be edited", function() {
            selectProject();
            selectActivity();
            cy.get("button[data-cy=activity-edit-button]").click({force: true})
            cy.get("div[data-form-attribute=" +
                "\"[:activity/estimated-start-date :activity/estimated-end-date]\"]").should("exist")
        })
    })
})
