describe("Activity view", function() {
    beforeEach(() => {
        cy.visit("")
        cy.get("#password-textfield").type(Cypress.env("SITE_PASSWORD"));
        cy.get("button").contains("Login as Benjamin Boss").click();
        cy.contains('Minu projektid');
    })

    function selectProject() {
        cy.get("span").contains("menu").click({force: true});

        cy.get("span").contains("Projektide loetelu").click();
        cy.contains("Kõik projektid").click();

        cy.get("#quick-search").type("aruvalla");
        cy.get("p").contains("Aruvalla").click();
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
