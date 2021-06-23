context('Land owner opinions', function() {
    describe('Hooks', () => {

        it("Owner opinions html opens", function() {
            navigateToProjectLand(this.projectID)

            cy.get("#project-export-menu").click();
            cy.get("#owner-opinion-export").click();

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

        after(() => {
            // THIS IS CALLED JUST SO LOCAL RUNTIME ISN'T BROKEN AFTER TEST
            cy.teardown("mock-estate-infos", {})
        })
    });

    beforeEach(() => {
        cy.request({method: "POST",
            url: "/testsetup/task",
            body: {"project-name": "land owner opinion TESTING"}})
            .then((response) => {
                cy.wrap(response.body["project-id"]).as("projectID")
            })

        cy.setup("task", {"project-name": "Land owner opinions testing",
                          "activity": "preliminary-design"})

        cy.setup("mock-estate-infos", {})

        cy.dummyLogin("Danny");
    })

    function navigateToProjectLand(projectId) {
        cy.visit("#/projects/"+projectId);
        cy.get("[data-cy='project-menu']").click({force: true});
        cy.get("li#navigation-item-land").click({force: true})

    }




});
