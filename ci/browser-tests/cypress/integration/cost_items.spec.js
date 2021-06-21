describe("Task view", function() {
  before(() => {
    cy.randomName("assetname", "myasset")
    cy.dummyLogin("benjamin")
    cy.selectLanguage("#EN")
    cy.projectByName("integration test project")
    cy.get("button.project-menu").click({force: true})
    cy.get("li#navigation-item-cost-items").click({force: true})

  })

  context("Assets", function() {

    it("new asset can be added", function() {
      cy.get("[data-cy='add-cost-item']").click()

      // Select Road drainage / Culvert
      cy.get("[data-cy='select-fgroup-and-fclass']").type("culvert")
      cy.get("div.select-user-entry.active").click()

      // Insert location information
      cy.get("div[data-form-attribute=':location/start-point'] input").type("600236.0080478053,6557242.769895551")
      cy.get("div[data-form-attribute=':location/end-point'] input").type("600274.183778265,6557247.541861858")

      // Fill required data
      cy.get("select[id='links-type-select:common/status']").select("Planned")
      cy.get("select[id='links-type-select:culvert/culvertlocation']").select("Under main road")

      // Submit
      cy.get("button[type=submit]").click()


    })
  })
})
