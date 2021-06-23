describe("Cost items, totals and materials views", function() {
  let costItemOid
  let componentOid
  let materialOid
  let projectId

  beforeEach(() => {
    cy.dummyLogin("benjamin")
    cy.selectLanguage("#EN")
    cy.projectByName("integration test project")
    cy.location().then((loc) => {
      let matches = loc.hash.match(/#\/projects\/(\d+)/)
      projectId = matches[1]
    })
    // TODO store projectId
  })

  context("Cost items", function() {

    it("new cost item can be created", function() {
      cy.get("button.project-menu").click({force: true})
      cy.get("li#navigation-item-cost-items").click({force: true})

      cy.get("[data-cy='add-cost-item']").click()

      // Select Road drainage / Culvert
      cy.get("[data-cy='select-fgroup-and-fclass']").type("culvert")
      cy.get("div.select-user-entry.active").click()

      // Insert location information
      cy.get("[data-form-attribute=':location/start-point'] input").type("600236.0080478053,6557242.769895551")
      cy.get("[data-form-attribute=':location/end-point'] input").type("600274.183778265,6557247.541861858")

      // Fill required data
      cy.get("select[id='links-type-select:common/status']").select("Planned")
      cy.get("select[id='links-type-select:culvert/culvertlocation']").select("Under main road")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the cost item form, store cost item oid
      cy.get("h2[data-cy='cost-item-oid']").then(($h2) => {
        costItemOid = $h2.text()
      })
    })

    it("cost item can be accessed using the left panel navigation", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/")
      // Navigate to the created culvert cost item via the navigation on the left panel
      cy.get("[data-cy='navigation-fgroup-drainage'] button").click()
      cy.get("[data-cy='navigation-fclass-culvert'] a").contains(costItemOid).click()
      cy.get("[data-cy='cost-item-oid']").contains(costItemOid)
    })

    it("component can be added to the cost item", function() {
      cy.visit("#/projects/" + projectId + "/cost-items/" + costItemOid)

      // Add a culvert pipe component
      cy.get("button[data-cy='add-culvertpipe']").click()
      cy.get("[data-form-attribute=':culvertpipe/culvertpipediameter'] input").type("10")
      cy.get("[data-form-attribute=':component/quantity'] input").type("20")
      cy.get("select[id='links-type-select:common/status']").select("Planned")
      cy.get("[data-form-attribute=':culvertpipe/culvertpipelenght']").type("20")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the component form, store component oid
      cy.get("h2[data-cy='component-oid']").then(($h2) => {
        componentOid = $h2.text()
      })
    })

    it("material can be added to the added component", function() {
      // Open the component page
      cy.visit("#/projects/" + projectId + "/cost-items/" + componentOid)

      // Add concrete material
      cy.get("button[data-cy='component-menu-button']").click()
      cy.get("li[data-cy='add-concrete']").click()
      cy.get("[data-form-attribute=':concrete/concreteclass'] select").select("C25")
      cy.get("[data-form-attribute=':concrete/concreteenvironmentalclass'] select").select("X0")

      // Submit
      cy.get("button[type=submit]").click()

      // Check we're redirected to the material form, store material oid
      cy.get("h2[data-cy='material-oid']").then(($h2) => {
        materialOid = $h2.text()
      })

    })

  })
})
