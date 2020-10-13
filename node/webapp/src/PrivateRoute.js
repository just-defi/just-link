import React from 'react'
import { Route, Redirect } from 'react-router-dom'
import { connect } from 'react-redux'

 class PrivateRoute extends Route {
  constructor(...args) {
    super(...args);
  }


  render() {
    return (this.props.authentication || this.props.path == '/login') ? (
      super.render()
    ) : (
      <Redirect to="/login" />
    )
  }
}

const mapStateToProps = state => {
  return {
    authentication: state.app.authentication,
  }
}

export default connect(mapStateToProps)(PrivateRoute)
