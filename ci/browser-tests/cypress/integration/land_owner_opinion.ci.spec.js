context('Land owner opinions', function() {
    beforeEach(() => {
        cy.request({method: "POST",
            url: "/testsetup/task",
            body: {"project-name": "land owner opinion TESTING"}})
            .then((response) => {
                cy.wrap(response.body["project-id"]).as("projectID")
            })

        cy.setup("task", {"project-name": "Land owner opinions testing",
                          "activity": "preliminary-design"})

        cy.dummyLogin("Danny");
    })

    function navigateToProjectLand(projectId) {
        cy.visit("#/projects/"+projectId);
        cy.get("button.project-menu").click({force: true});
        cy.get("li#navigation-item-land").click({force: true})

    }


    it("Owner opinions html opens", function() {
        navigateToProjectLand(this.projectID)

        cy.get("#project-export-menu").click();
        cy.get("#owner-opinion-export").click();

        cy.wait(1000);

        cy.get('div[data-form-attribute=":land-owner-opinion/activity"] select').select("0") // use generic form selection because options are not enum attributes
        cy.formInput(
            ":land-owner-opinion/type", "[:land-owner-opinion.type/design]"
        );
        cy.get("a#view-export-html").should("not.have.class", "Mui-disabled")
        // visit the page
        cy.get("a#view-export-html").then(($link) => {
            cy.visit($link.attr("href"))
        })

        cy.get("h2").contains("land owner opinion TESTING"); // export project name to be found from the document

    });

});
